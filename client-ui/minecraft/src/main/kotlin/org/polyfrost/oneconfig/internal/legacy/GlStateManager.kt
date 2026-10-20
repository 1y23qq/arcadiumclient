package org.polyfrost.oneconfig.internal.legacy

//? if = 1.7.10 {
/*import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.system.MemoryStack

object GlStateManager {
    enum class SourceFactor(val value: Int) {
        ZERO(GL11.GL_ZERO), ONE(GL11.GL_ONE), SRC_COLOR(GL11.GL_SRC_COLOR),
        ONE_MINUS_SRC_COLOR(GL11.GL_ONE_MINUS_SRC_COLOR), SRC_ALPHA(GL11.GL_SRC_ALPHA),
        ONE_MINUS_SRC_ALPHA(GL11.GL_ONE_MINUS_SRC_ALPHA), DST_ALPHA(GL11.GL_DST_ALPHA),
        ONE_MINUS_DST_ALPHA(GL11.GL_ONE_MINUS_DST_ALPHA), DST_COLOR(GL11.GL_DST_COLOR),
        ONE_MINUS_DST_COLOR(GL11.GL_ONE_MINUS_DST_COLOR), SRC_ALPHA_SATURATE(GL11.GL_SRC_ALPHA_SATURATE)
    }

    enum class DestFactor(val value: Int) {
        ZERO(GL11.GL_ZERO), ONE(GL11.GL_ONE), SRC_COLOR(GL11.GL_SRC_COLOR),
        ONE_MINUS_SRC_COLOR(GL11.GL_ONE_MINUS_SRC_COLOR), SRC_ALPHA(GL11.GL_SRC_ALPHA),
        ONE_MINUS_SRC_ALPHA(GL11.GL_ONE_MINUS_SRC_ALPHA), DST_ALPHA(GL11.GL_DST_ALPHA),
        ONE_MINUS_DST_ALPHA(GL11.GL_ONE_MINUS_DST_ALPHA), DST_COLOR(GL11.GL_DST_COLOR),
        ONE_MINUS_DST_COLOR(GL11.GL_ONE_MINUS_DST_COLOR)
    }

    class BooleanState(private val capability: Int) {
        val enabled get() = GL11.glIsEnabled(capability)
    }
    class DepthState {
        val state = BooleanState(GL11.GL_DEPTH_TEST)
        val mask get() = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
    }
    class AlphaState { val state = BooleanState(GL11.GL_ALPHA_TEST) }
    class TextureState {
        val state = BooleanState(GL11.GL_TEXTURE_2D)
        val texture get() = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
    }
    class BlendState {
        val state = BooleanState(GL11.GL_BLEND)
        val sfactorRGB get() = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val dfactorRGB get() = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val sfactorAlpha get() = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val dfactorAlpha get() = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
    }
    class ColorState(val r: Float, val g: Float, val b: Float, val a: Float)

    val DEPTH = DepthState()
    val ALPHA_TEST = AlphaState()
    val LIGHTING = BooleanState(GL11.GL_LIGHTING)
    val BLEND = BlendState()
    val TEXTURES = Array(32) { TextureState() }
    val texture get() = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) - GL13.GL_TEXTURE0
    @get:JvmStatic val shadeModel get() = GL11.glGetInteger(GL11.GL_SHADE_MODEL)
    val COLOR get() = MemoryStack.stackPush().use { stack ->
        val rgba = stack.mallocFloat(4)
        GL11.glGetFloatv(GL11.GL_CURRENT_COLOR, rgba)
        ColorState(rgba[0], rgba[1], rgba[2], rgba[3])
    }

    @JvmStatic fun activeTexture(texture: Int) = GL13.glActiveTexture(texture)
    @JvmStatic fun alphaFunc(function: Int, reference: Float) = GL11.glAlphaFunc(function, reference)
    @JvmStatic fun bindTexture(texture: Int) = GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
    @JvmStatic fun blendFunc(source: Int, destination: Int) = GL11.glBlendFunc(source, destination)
    @JvmStatic fun blendFunc(source: SourceFactor, destination: DestFactor) = blendFunc(source.value, destination.value)
    @JvmStatic fun blendFuncSeparate(sourceRgb: Int, destinationRgb: Int, sourceAlpha: Int, destinationAlpha: Int) =
        GL14.glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha)
    @JvmStatic fun blendFuncSeparate(sourceRgb: SourceFactor, destinationRgb: DestFactor, sourceAlpha: SourceFactor, destinationAlpha: DestFactor) =
        blendFuncSeparate(sourceRgb.value, destinationRgb.value, sourceAlpha.value, destinationAlpha.value)
    @JvmStatic fun clear(mask: Int) = GL11.glClear(mask)
    @JvmStatic fun clearColor(r: Float, g: Float, b: Float, a: Float) = GL11.glClearColor(r, g, b, a)
    @JvmStatic fun clearDepth(depth: Double) = GL11.glClearDepth(depth)
    @JvmStatic fun color4f(r: Float, g: Float, b: Float, a: Float) = GL11.glColor4f(r, g, b, a)
    @JvmStatic fun colorMask(r: Boolean, g: Boolean, b: Boolean, a: Boolean) = GL11.glColorMask(r, g, b, a)
    @JvmStatic fun depthFunc(function: Int) = GL11.glDepthFunc(function)
    @JvmStatic fun depthMask(enabled: Boolean) = GL11.glDepthMask(enabled)
    @JvmStatic fun disableAlphaTest() = GL11.glDisable(GL11.GL_ALPHA_TEST)
    @JvmStatic fun enableAlphaTest() = GL11.glEnable(GL11.GL_ALPHA_TEST)
    @JvmStatic fun disableBlend() = GL11.glDisable(GL11.GL_BLEND)
    @JvmStatic fun enableBlend() = GL11.glEnable(GL11.GL_BLEND)
    @JvmStatic fun disableColorMaterial() = GL11.glDisable(GL11.GL_COLOR_MATERIAL)
    @JvmStatic fun enableColorMaterial() = GL11.glEnable(GL11.GL_COLOR_MATERIAL)
    @JvmStatic fun disableCull() = GL11.glDisable(GL11.GL_CULL_FACE)
    @JvmStatic fun enableCull() = GL11.glEnable(GL11.GL_CULL_FACE)
    @JvmStatic fun disableDepthTest() = GL11.glDisable(GL11.GL_DEPTH_TEST)
    @JvmStatic fun enableDepthTest() = GL11.glEnable(GL11.GL_DEPTH_TEST)
    @JvmStatic fun disableLighting() = GL11.glDisable(GL11.GL_LIGHTING)
    @JvmStatic fun enableLighting() = GL11.glEnable(GL11.GL_LIGHTING)
    @JvmStatic fun disableRescaleNormal() = GL11.glDisable(GL13.GL_RESCALE_NORMAL)
    @JvmStatic fun enableRescaleNormal() = GL11.glEnable(GL13.GL_RESCALE_NORMAL)
    @JvmStatic fun disableTexture() = GL11.glDisable(GL11.GL_TEXTURE_2D)
    @JvmStatic fun enableTexture() = GL11.glEnable(GL11.GL_TEXTURE_2D)
    @JvmStatic fun loadIdentity() = GL11.glLoadIdentity()
    @JvmStatic fun matrixMode(mode: Int) = GL11.glMatrixMode(mode)
    @JvmStatic fun ortho(left: Double, right: Double, bottom: Double, top: Double, near: Double, far: Double) =
        GL11.glOrtho(left, right, bottom, top, near, far)
    @JvmStatic fun popMatrix() = GL11.glPopMatrix()
    @JvmStatic fun pushMatrix() = GL11.glPushMatrix()
    @JvmStatic fun rotatef(angle: Float, x: Float, y: Float, z: Float) = GL11.glRotatef(angle, x, y, z)
    @JvmStatic fun scalef(x: Float, y: Float, z: Float) = GL11.glScalef(x, y, z)
    @JvmStatic fun shadeModel(mode: Int) = GL11.glShadeModel(mode)
    @JvmStatic fun translated(x: Double, y: Double, z: Double) = GL11.glTranslated(x, y, z)
    @JvmStatic fun translatef(x: Float, y: Float, z: Float) = GL11.glTranslatef(x, y, z)
    @JvmStatic fun viewport(x: Int, y: Int, width: Int, height: Int) = GL11.glViewport(x, y, width, height)
}
*///?}
