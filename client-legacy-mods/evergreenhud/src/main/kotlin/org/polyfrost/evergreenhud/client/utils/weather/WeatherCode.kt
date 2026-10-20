package org.polyfrost.evergreenhud.client.utils.weather

enum class WeatherCode(val code: Int, val description: String) {
    CLEAR(0, "Clear"),
    MAINLY_CLEAR(1, "Mainly Clear"),
    PARTLY_CLOUDY(2, "Partly Cloudy"),
    OVERCAST(3, "Overcast"),
    FOG(45, "Fog"),
    RIME_FOG(48, "Rime Fog"),
    LIGHT_DRIZZLE(51, "Light Drizzle"),
    MODERATE_DRIZZLE(53, "Drizzle"),
    DENSE_DRIZZLE(55, "Heavy Drizzle"),
    LIGHT_FREEZING_DRIZZLE(56, "Light Freezing Drizzle"),
    DENSE_FREEZING_DRIZZLE(57, "Freezing Drizzle"),
    SLIGHT_RAIN(61, "Light Rain"),
    MODERATE_RAIN(63, "Rain"),
    HEAVY_RAIN(65, "Heavy Rain"),
    LIGHT_FREEZING_RAIN(66, "Light Freezing Rain"),
    HEAVY_FREEZING_RAIN(67, "Freezing Rain"),
    SLIGHT_SNOW(71, "Light Snow"),
    MODERATE_SNOW(73, "Snow"),
    HEAVY_SNOW(75, "Heavy Snow"),
    SNOW_GRAINS(77, "Snow Grains"),
    RAIN_SHOWER_SLIGHT(80, "Light Showers"),
    RAIN_SHOWER_MODERATE(81, "Showers"),
    RAIN_SHOWER_VIOLENT(82, "Heavy Showers"),
    SNOW_SHOWER_SLIGHT(85, "Light Snow Showers"),
    SNOW_SHOWER_HEAVY(86, "Snow Showers"),
    THUNDERSTORM(95, "Thunderstorm"),
    SLIGHT_HAIL_THUNDERSTORM(96, "Thunderstorm with Hail"),
    HEAVY_HAIL_THUNDERSTORM(99, "Thunderstorm with Heavy Hail"),
    UNKNOWN(-1, "Unknown");

    companion object {
        fun fromCode(code: Int): WeatherCode = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}
