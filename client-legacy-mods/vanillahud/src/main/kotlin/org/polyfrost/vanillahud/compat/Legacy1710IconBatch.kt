package org.polyfrost.vanillahud.compat

//? if = 1.7.10 {
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL15
import org.lwjgl.system.MemoryUtil

object LegacyIconBatch {
    private val vertices = BufferUtils.createFloatBuffer(16 * 1024)

    @JvmStatic
    fun quad(x: Float, y: Float, u: Float, v: Float, sourceWidth: Int, sourceHeight: Int,
             width: Int, height: Int, textureWidth: Float, textureHeight: Float, z: Float, turns: Int) {
        if (vertices.remaining() < 20) draw()
        val u0 = u / textureWidth
        val v0 = v / textureHeight
        val u1 = (u + sourceWidth) / textureWidth
        val v1 = (v + sourceHeight) / textureHeight
        val cx = x + width / 2f
        val cy = y + height / 2f
        val steps = Math.floorMod(-turns, 4)
        corner(x, y + height, u0, v1, cx, cy, steps, z)
        corner(x + width, y + height, u1, v1, cx, cy, steps, z)
        corner(x + width, y, u1, v0, cx, cy, steps, z)
        corner(x, y, u0, v0, cx, cy, steps, z)
    }

    private fun corner(x: Float, y: Float, u: Float, v: Float, cx: Float, cy: Float, steps: Int, z: Float) {
        var dx = x - cx
        var dy = y - cy
        repeat(steps) {
            val old = dx
            dx = -dy
            dy = old
        }
        vertices.put(cx + dx).put(cy + dy).put(z).put(u).put(v)
    }

    @JvmStatic
    fun draw() {
        if (vertices.position() == 0) return
        val count = vertices.position() / 5
        vertices.flip()
        val previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT)
        try {
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0)
            GL13.glClientActiveTexture(GL13.GL_TEXTURE0)
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY)
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
            GL11.glDisableClientState(GL11.GL_COLOR_ARRAY)
            GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY)
            val address = MemoryUtil.memAddress(vertices)
            GL11.glVertexPointer(3, GL11.GL_FLOAT, 20, address)
            GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 20, address + 12)
            GL11.glDrawArrays(GL11.GL_QUADS, 0, count)
        } finally {
            GL11.glPopClientAttrib()
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer)
            vertices.clear()
        }
    }
}
//?}
