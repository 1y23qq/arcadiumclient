package org.polyfrost.oneconfig.internal.ui.components

fun arcadiumLabel(value: Any): Any {
    val text = value.asRenderText()
    return when (text.lowercase().replace(" ", "").replace("-", "")) {
        "oneconfig", "oneconfigv1" -> "Arcadium Options"
        "oneclient", "polyplus", "polyplus+", "poly+", "arcadium(arcadiumclient)" -> "Arcadium Client"
        "polyhitbox" -> "Hitboxes"
        "polysprint" -> "Toggle Sprint"
        "polynametag" -> "Nametags"
        "polytime" -> "Time"
        "polyweather" -> "Weather"
        "polyzoom" -> "Zoom"
        "polyblur" -> "Motion Blur"
        "customblockhighlight", "blockhighlight" -> "Block Overlay"
        else -> arcadiumText(text).takeIf { it != text } ?: value
    }
}

fun arcadiumText(text: String): String = text
    .replace("OneClient", "Arcadium Client")
    .replace("OneConfig", "Arcadium Options")
    .replace("PolyPlus", "Arcadium Features")
    .replace("PolyGlass", "Glass")
    .replace("PolyHitbox", "Hitboxes")
    .replace("PolySprint", "Toggle Sprint")
    .replace("PolyNametag", "Nametags")
    .replace("PolyTime", "Time")
    .replace("PolyWeather", "Weather")
    .replace("PolyZoom", "Zoom")
    .replace("PolyBlur", "Motion Blur")
    .replace("Block Highlight", "Block Overlay")
