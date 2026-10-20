plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.7.10" /* [SC] DO NOT EDIT */

stonecutter handlers {
    inherit("aw", "ct")
}

stonecutter parameters {
    replacements.string(current.version == "1.7.10") {
        replace("net.minecraft.client.render.platform.GlStateManager", "org.polyfrost.oneconfig.internal.legacy.GlStateManager")
        replace("new EditBox(0, this.font,", "new EditBox(this.font,")
        replace("Screen.isAltDown()", "(org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LMENU) || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RMENU))")
        replace("Lnet/minecraft/client/render/platform/GlStateManager;pushMatrix()V", "Lorg/lwjgl/opengl/GL11;glPushMatrix()V")
        replace("Lnet/minecraft/client/gui/screens/ChatScreen;sendChatMessage(Ljava/lang/String;)V", "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;sendChat(Ljava/lang/String;)V")
    }
    replacements.string {
        direction = eval(current.version, "< 1.21.11")
        from = "net.minecraft.Util"
        to = "net.minecraft.util.Util"
    }

    replacements.string {
        direction = eval(current.version, ">=26.1")
        from = "classTweaker v2 named"
        to = "classTweaker v2 official"
    }

    replacements.string(eval(current.version, "<= 1.8.9")) {
        replace("import com.mojang.blaze3d.platform.InputConstants", "import org.polyfrost.oneconfig.internal.legacy.InputConstants")
        replace("import com.mojang.blaze3d.platform.NativeImage", "import org.polyfrost.oneconfig.internal.legacy.NativeImage")
        replace("import com.mojang.blaze3d.platform.Window", "import org.polyfrost.oneconfig.internal.legacy.Window")
        replace("import net.minecraft.client.gui.GuiGraphics", "import org.polyfrost.chatting.compat.GuiGraphics")
        replace("import com.mojang.blaze3d.vertex.PoseStack", "import org.polyfrost.chatting.compat.PoseStack")
        replace("import com.mojang.blaze3d.systems.RenderSystem", "import org.polyfrost.chatting.compat.RenderSystem")
        replace("import net.minecraft.client.gui.components.PlayerFaceRenderer", "import org.polyfrost.chatting.compat.PlayerFaceRenderer")
    }

    replacements.string(eval(current.version, ">=26.3")) {
        replace("com.mojang.blaze3d.buffers", "com.mojang.renderpearl.api.buffers")
        replace("com.mojang.blaze3d.textures", "com.mojang.renderpearl.api.textures")
        replace("com.mojang.blaze3d.GpuFormat", "com.mojang.renderpearl.api.GpuFormat")
        replace("com.mojang.blaze3d.vertex.VertexFormat", "com.mojang.renderpearl.api.vertex.VertexFormat")
        replace("com.mojang.blaze3d.systems.GpuDevice", "com.mojang.renderpearl.api.device.GpuDevice")
        replace("com.mojang.blaze3d.systems.CommandEncoder", "com.mojang.renderpearl.api.commands.CommandEncoder")
    }
}

stonecutter tasks {
    order("publishModrinth")
}
