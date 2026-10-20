package org.polyfrost.vanillahud.hud

//? if = 1.7.10 {
import net.minecraft.client.entity.living.player.ClientPlayerEntity
import net.minecraft.client.network.PlayerInfo
import net.minecraft.resources.ResourceLocation
import net.minecraft.scoreboard.team.Team
import org.lwjgl.opengl.GL11
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import org.polyfrost.vanillahud.compat.LegacyDrawContext
import org.polyfrost.vanillahud.render.HudTransform
import org.polyfrost.vanillahud.util.TabListManager
import kotlin.math.max
import kotlin.math.min

object LegacyTabRenderer {
    private data class Entry(val info: PlayerInfo, val name: String, val skin: ResourceLocation?, val score: String?)
    private data class Layout(val entries: List<Entry>, val columns: Int, val rows: Int,
                              val cellWidth: Int, val pingWidth: Int, val width: Int, val height: Int)

    private val defaultSkin = ResourceLocation("textures/entity/steve.png")
    private val icons = ResourceLocation("textures/gui/icons.png")
    private val skinLookup by lazy {
        runCatching {
            Class.forName("org.polyfrost.chatting.compat.LegacySkinLookup", false, javaClass.classLoader)
                .getMethod("texture", String::class.java)
        }.getOrNull()
    }
    private var layout: Layout? = null
    private var layoutOptions = 0
    private var toggleHeld = false
    private var toggled = false

    @JvmStatic
    fun updateKey(down: Boolean) {
        val hud = Huds.tabList
        if (hud.displayMode != 0 && down && !toggleHeld) toggled = !toggled
        toggleHeld = down
        hud.updateOpen(hud.previewing || if (hud.displayMode == 0) down else toggled)
    }

    @JvmStatic
    fun refresh() {
        val hud = Huds.tabList
        if (!hud.shouldDraw() || !hud.shouldShow()) {
            layout = null
            return
        }
        layout = buildLayout(hud)
        layoutOptions = options(hud)
    }

    private fun options(hud: TabListHud): Int {
        var flags = 0
        if (hud.showHead) flags = flags or 1
        if (hud.showPing) flags = flags or 2
        if (hud.numberPing) flags = flags or 4
        if (hud.selfAtTop) flags = flags or 8
        if (hud.previewing) flags = flags or 16
        return ((hud.playerLimit * 31 + flags) * 31 + hud.pingType) * 31 + LegacyDrawContext.guiWidth()
    }

    private fun current(hud: TabListHud): Layout {
        val key = options(hud)
        if (layout == null || key != layoutOptions) {
            layout = buildLayout(hud)
            layoutOptions = key
        }
        return layout!!
    }

    private fun buildLayout(hud: TabListHud): Layout {
        val names = if (hud.previewing) TabListManager.devInfo
                    else mc.networkHandler?.onlinePlayers?.filterIsInstance<PlayerInfo>().orEmpty()
        val world = mc.level
        val self = mc.player?.name
        val objective = world?.scoreboard?.getDisplayObjective(0)
        val entries = names.sortedWith(compareBy<PlayerInfo>(
            { if (hud.selfAtTop && it.name == self) 0 else 1 },
            { world?.scoreboard?.getTeamOfMember(it.name)?.name ?: "" },
            { it.name.lowercase() },
        )).take(hud.playerLimit.coerceIn(10, 120)).map { info ->
            val team = world?.scoreboard?.getTeamOfMember(info.name)
            val name = Team.getMemberDisplayName(team, info.name)
            val entity = world?.getPlayer(info.name) as? ClientPlayerEntity
            val score = objective?.scoreboard?.getScore(info.name, objective)?.get()?.toString()
            val skin = entity?.skinTextureLocation ?: runCatching {
                skinLookup?.invoke(null, info.name) as? ResourceLocation
            }.getOrNull()
            Entry(info, name, skin, score)
        }
        val count = entries.size
        var columns = 1
        var rows = count
        while (rows > 20) {
            columns++
            rows = (count + columns - 1) / columns
        }
        val font = mc.font
        val nameWidth = entries.maxOfOrNull { measurePlayerName(it.info, it.name) } ?: 60
        val scoreWidth = entries.maxOfOrNull { it.score?.let { score -> mc.font.width(score) + 8 } ?: 0 } ?: 0
        val headWidth = if (hud.showHead) 9 else 0
        val pingWidth = if (!hud.showPing) 0 else if (hud.numberPing) {
            val maximum = entries.maxOfOrNull { font.width(it.info.ping.toString()) } ?: font.width("999")
            if (hud.pingType == 0) maximum / 2 + 3 else maximum + 3
        } else 13
        val available = LegacyDrawContext.guiWidth() - 50 - (columns - 1) * 5
        val cell = min(headWidth + nameWidth + scoreWidth + max(13, pingWidth), max(1, available / columns))
        return Layout(entries, columns, rows, cell, pingWidth, cell * columns + (columns - 1) * 5, rows * 9)
    }

    fun measuredWidth(hud: TabListHud) = (current(hud).width + 2).toFloat()
    fun measuredHeight(hud: TabListHud) = (current(hud).height + 2).toFloat()

    @JvmStatic
    fun render() {
        val hud = Huds.tabList
        if (!hud.shouldDraw() || !hud.shouldShow()) return
        val data = current(hud)
        if (data.entries.isEmpty()) return
        val left = (LegacyDrawContext.guiWidth() - data.width) / 2
        val top = 10
        HudTransform.begin(LegacyDrawContext, hud)
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT or GL11.GL_COLOR_BUFFER_BIT or GL11.GL_CURRENT_BIT or GL11.GL_TEXTURE_BIT)
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST)
            GL11.glEnable(GL11.GL_BLEND)
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA)
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            GL11.glBegin(GL11.GL_QUADS)
            drawBackground(hud.bodyBgArgb, left - 1, top - 1, data.width + 2, data.height + 2)
            for (index in data.entries.indices) {
                val x = left + index / data.rows * (data.cellWidth + 5)
                val y = top + index % data.rows * 9
                drawBackground(hud.tabWidgetArgb, x, y, data.cellWidth, 8)
            }
            GL11.glEnd()
            GL11.glEnable(GL11.GL_TEXTURE_2D)
            GL11.glColor4f(1f, 1f, 1f, 1f)
            if (hud.showHead) {
                for ((index, entry) in data.entries.withIndex()) {
                    val x = left + index / data.rows * (data.cellWidth + 5)
                    val y = top + index % data.rows * 9
                    mc.textureManager.bind(entry.skin ?: defaultSkin)
                    GL11.glBegin(GL11.GL_QUADS)
                    texturedQuad(x.toFloat(), y.toFloat(), 8f, 8f, 8f / 64f, 8f / 32f, 16f / 64f, 16f / 32f)
                    val hatSize = if (hud.betterHatLayer) 9f else 8f
                    val hatOffset = if (hud.betterHatLayer) -0.5f else 0f
                    texturedQuad(x + hatOffset, y + hatOffset, hatSize, hatSize, 40f / 64f, 8f / 32f, 48f / 64f, 16f / 32f)
                    GL11.glEnd()
                }
            }
            for ((index, entry) in data.entries.withIndex()) {
                val x = left + index / data.rows * (data.cellWidth + 5)
                val y = top + index % data.rows * 9
                drawPlayerName(entry.info, entry.name, x + if (hud.showHead) 9 else 0, y, hud.textType != 0)
                entry.score?.let { score ->
                    val scoreX = x + data.cellWidth - data.pingWidth - mc.font.width(score) - 3
                    val nameEnd = x + (if (hud.showHead) 9 else 0) + measurePlayerName(entry.info, entry.name)
                    if (scoreX - nameEnd > 5) mc.font.draw(score, scoreX, y, 0xFFFFFF55.toInt(), hud.textType != 0)
                }
                if (!hud.showPing || hud.hidesPing(entry.info.ping)) continue
                val ping = entry.info.ping
                if (hud.numberPing) {
                    val text = ping.toString()
                    val small = hud.pingType == 0
                    val width = mc.font.width(text)
                    if (small) {
                        GL11.glPushMatrix()
                        GL11.glTranslatef((x + data.cellWidth - width / 2 - 1).toFloat(), (y + 2).toFloat(), 0f)
                        GL11.glScalef(0.5f, 0.5f, 1f)
                        mc.font.draw(text, 0, 0, hud.pingColor(ping), hud.textType != 0)
                        GL11.glPopMatrix()
                    } else mc.font.draw(text, x + data.cellWidth - width - 1, y, hud.pingColor(ping), hud.textType != 0)
                } else {
                    val level = when { ping < 0 -> 5; ping < 150 -> 0; ping < 300 -> 1; ping < 600 -> 2; ping < 1000 -> 3; else -> 4 }
                    mc.textureManager.bind(icons)
                    GL11.glColor4f(1f, 1f, 1f, 1f)
                    texture(x + data.cellWidth - 11, y, 10, 8, 0f, (176 + level * 8) / 256f, 10f / 256f, (184 + level * 8) / 256f)
                }
            }
        } finally {
            GL11.glPopAttrib()
            HudTransform.end(LegacyDrawContext)
        }
    }

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun measurePlayerName(info: PlayerInfo, name: String): Int = mc.font.width(name)

    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun drawPlayerName(info: PlayerInfo, name: String, x: Int, y: Int, shadow: Boolean) {
        mc.font.draw(name, x, y, -1, shadow)
    }

    @JvmStatic
    fun drawBackground(argb: Int, x: Int, y: Int, width: Int, height: Int) {
        if (argb ushr 24 == 0) return
        color(argb)
        rectangle(x, y, width, height)
    }

    private fun color(argb: Int) = GL11.glColor4f((argb ushr 16 and 255) / 255f, (argb ushr 8 and 255) / 255f,
                                                (argb and 255) / 255f, (argb ushr 24) / 255f)

    private fun rectangle(x: Int, y: Int, width: Int, height: Int) {
        GL11.glVertex2i(x, y + height)
        GL11.glVertex2i(x + width, y + height)
        GL11.glVertex2i(x + width, y)
        GL11.glVertex2i(x, y)
    }

    private fun texture(x: Int, y: Int, width: Int, height: Int, u0: Float, v0: Float, u1: Float, v1: Float) {
        GL11.glBegin(GL11.GL_QUADS)
        texturedQuad(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), u0, v0, u1, v1)
        GL11.glEnd()
    }

    private fun texturedQuad(x: Float, y: Float, width: Float, height: Float, u0: Float, v0: Float, u1: Float, v1: Float) {
        GL11.glTexCoord2f(u0, v1); GL11.glVertex2f(x, y + height)
        GL11.glTexCoord2f(u1, v1); GL11.glVertex2f(x + width, y + height)
        GL11.glTexCoord2f(u1, v0); GL11.glVertex2f(x + width, y)
        GL11.glTexCoord2f(u0, v0); GL11.glVertex2f(x, y)
    }
}
//?}
