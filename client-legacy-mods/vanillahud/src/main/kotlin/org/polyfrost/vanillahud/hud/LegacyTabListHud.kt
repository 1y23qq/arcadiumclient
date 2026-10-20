package org.polyfrost.vanillahud.hud

//? if = 1.7.10 {
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.api.config.v1.annotations.*
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import org.polyfrost.vanillahud.compat.TabListCompat

class TabListHud : VanillaHud("vanillahud-tab.json", "Tab List", Category.INFO) {
    @Slider(
        title = "Tab Player Limit",
        description = "How many players can display on the tab list.",
        min = 10f,
        max = 120f
    )
    var playerLimit = 80

    @Dropdown(title = "Mode", options = ["Held", "Toggle"])
    var displayMode = 0

    @Switch(title = "Animation", description = "Slide the tab list open and closed instead of snapping.")
    var animation = true

    @Slider(
        title = "Animation Duration",
        description = "How long the open / close animation takes, in milliseconds.",
        min = 50f,
        max = 1000f
    )
    var animationDuration = 400f

    @Dropdown(title = "Text Shadow", options = ["No Shadow", "Shadow"])
    var textType: Int = 1

    @Switch(title = "Show Header")
    var showHeader: Boolean = true

    @Switch(title = "Show Footer")
    var showFooter: Boolean = true

    @Switch(title = "Show Self At Top")
    var selfAtTop: Boolean = false

    @Switch(title = "Show Player's Head")
    var showHead: Boolean = true

    @Switch(title = "Better Hat Layer")
    var betterHatLayer: Boolean = true

    @Switch(title = "Show Player's Ping")
    var showPing: Boolean = true

    @Switch(title = "Use Number Ping")
    var numberPing: Boolean = true

    @Dropdown(title = "Ping Text", options = ["Small", "Full"])
    var pingType = 1

    @Switch(
        title = "Hide False Ping",
        description = "Hides falsified ping numbers such as a ping of 0 or 1 when on Hypixel"
    )
    var hideFalsePing: Boolean = true

    @Color(title = "Ping Between 0 and 75")
    var pingLevelOne = PolyColor(0xFF55FF55.toInt())

    @Color(title = "Ping Between 75 and 145")
    var pingLevelTwo = PolyColor(0xFF00AA00.toInt())

    @Color(title = "Ping Between 145 and 200")
    var pingLevelThree = PolyColor(0xFFFFFF55.toInt())

    @Color(title = "Ping Between 200 and 300")
    var pingLevelFour = PolyColor(0xFFFFAA00.toInt())

    @Color(title = "Ping Between 300 and 400")
    var pingLevelFive = PolyColor(0xFFFF5555.toInt())

    @Color(title = "Ping Above 400")
    var pingLevelSix = PolyColor(0xFFAA0000.toInt())

    @Color(title = "Tab Widget Color")
    var tabWidgetColor = PolyColor(0x20FFFFFF.toInt())

    @Color(title = "Header Background Color")
    var headerBgColor = PolyColor(0x80000000.toInt())

    @Color(title = "Body Background Color")
    var bodyBgColor = PolyColor(0x80000000.toInt())

    @Color(title = "Footer Background Color")
    var footerBgColor = PolyColor(0x80000000.toInt())

    override fun setup() {
        super.setup()
        for (field in arrayOf("showHeader", "showFooter", "headerBgColor", "footerBgColor")) hideIf(field) { true }
    }

    val tabWidgetArgb: Int get() = tabWidgetColor.argb
    val headerBgArgb: Int get() = headerBgColor.argb
    val bodyBgArgb: Int get() = bodyBgColor.argb
    val footerBgArgb: Int get() = footerBgColor.argb

    fun hidesPing(ping: Int): Boolean {
        if (previewing) return false
        return hideFalsePing && (ping <= 1 || ping >= 999)
    }

    fun pingColor(ping: Int): Int = when {
        ping >= 400 -> pingLevelSix
        ping >= 300 -> pingLevelFive
        ping >= 200 -> pingLevelFour
        ping >= 145 -> pingLevelThree
        ping >= 75 -> pingLevelTwo
        else -> pingLevelOne
    }.argb

    override val naturalWidth get() = 200f
    override val naturalHeight get() = 100f
    override fun vanillaOriginX(screenWidth: Int, screenHeight: Int) = screenWidth / 2f - width / 2f
    override fun vanillaOriginY(screenWidth: Int, screenHeight: Int) = 10f
    override val anchorX get() = 0.5f
    override val anchorY get() = 0f

    val backgroundTop get() = 1f

    private var animOpen = false
    private var animStart = 0L
    private var animFrom = 0f
    private var animTo = 0f

    private fun easeOutQuart(x: Float): Float {
        val t = 1f - x
        return 1f - t * t * t * t
    }

    fun updateOpen(open: Boolean) {
        if (open == animOpen) return
        animOpen = open
        animFrom = clipFraction()
        animTo = if (open) 1f else 0f
        animStart = System.currentTimeMillis()
    }

    fun resetOpen() {
        animOpen = false
        animFrom = 0f
        animTo = 0f
        animStart = 0L
    }

    fun clipFraction(): Float {
        if (!animation) return if (animOpen) 1f else 0f
        val dur = animationDuration.coerceAtLeast(1f)
        val t = ((System.currentTimeMillis() - animStart).toFloat() / dur).coerceIn(0f, 1f)
        return animFrom + (animTo - animFrom) * easeOutQuart(t)
    }

    fun isRendering(): Boolean = animOpen || clipFraction() > 0.001f

    override fun shouldShow() = previewing || isRendering() || mc.options.playerListKey.isPressed
    override fun hasContent() = shouldShow()
    fun foreignBounds(): TabListCompat.Bounds? = null
    override fun measuredWidth() = LegacyTabRenderer.measuredWidth(this)
    override fun measuredHeight() = LegacyTabRenderer.measuredHeight(this)
}
//?}
