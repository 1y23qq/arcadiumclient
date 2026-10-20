package org.polyfrost.compose.mc

class MinecraftTextStyle(private val baseColor: Int) {
    private var colorCode: Char? = null
    var color = baseColor
        private set
    var bold = false
        private set
    var italic = false
        private set
    var underline = false
        private set
    var strikethrough = false
        private set
    var obfuscated = false
        private set

    fun apply(character: Char) {
        val code = character.lowercaseChar()
        when (code) {
            'k' -> obfuscated = true
            'l' -> bold = true
            'm' -> strikethrough = true
            'n' -> underline = true
            'o' -> italic = true
            else -> {
                bold = false
                italic = false
                underline = false
                strikethrough = false
                obfuscated = false
                val index = "0123456789abcdef".indexOf(code)
                colorCode = if (code == 'r') null else if (index < 0) 'f' else code
                color = if (code == 'r') baseColor else (baseColor and -0x1000000) or palette(if (index < 0) 15 else index)
            }
        }
    }

    fun formatPrefix(): String = buildString {
        colorCode?.let { append('§').append(it) }
        if (obfuscated) append("§k")
        if (bold) append("§l")
        if (strikethrough) append("§m")
        if (underline) append("§n")
        if (italic) append("§o")
    }

    private fun palette(index: Int): Int {
        val bright = (index shr 3 and 1) * 85
        val red = (index shr 2 and 1) * 170 + bright + if (index == 6) 85 else 0
        val green = (index shr 1 and 1) * 170 + bright
        val blue = (index and 1) * 170 + bright
        return (red shl 16) or (green shl 8) or blue
    }
}
