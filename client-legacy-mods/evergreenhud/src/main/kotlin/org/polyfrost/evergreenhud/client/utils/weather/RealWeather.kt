package org.polyfrost.evergreenhud.client.utils.weather

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.utils.v1.JsonUtils
import org.polyfrost.oneconfig.utils.v1.dsl.runAsync
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

object RealWeather {
    private val LOGGER = LoggerFactory.getLogger("EvergreenHUD/Weather")

    private const val REFRESH_SECONDS = 600L

    private const val CHECK_INTERVAL_TICKS = 200

    private const val MIN_RETRY_SECONDS = 60L
    private const val MAX_RETRY_SECONDS = 900L

    data class Conditions(
        val code: WeatherCode,
        val temperature: Double,
        val apparentTemperature: Double,
        val humidity: Int,
        val windSpeed: Double,
        val precipitation: Double,
        val isDay: Boolean,
    )

    @Volatile
    var conditions: Conditions? = null
        private set

    private val started = AtomicBoolean()

    private val fetching = AtomicBoolean()

    @Volatile
    private var fetchedAt = Instant.MIN

    @Volatile
    private var retryAfter = Instant.MIN

    @Volatile
    private var retryDelaySeconds = MIN_RETRY_SECONDS

    @Volatile
    private var location: Pair<Double, Double>? = null

    @Volatile
    private var override: Pair<Double, Double>? = null

    val activeLocation: Pair<Double, Double>?
        get() = override ?: location

    fun setLocationOverride(latitude: Double?, longitude: Double?) {
        val next = if (latitude != null && longitude != null &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
        ) latitude to longitude else null

        if (next == override) return
        override = next

        conditions = null
        fetchedAt = Instant.MIN
        retryAfter = Instant.MIN
        retryDelaySeconds = MIN_RETRY_SECONDS

        if (started.get()) runAsync { fetch() }
    }

    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return

        var tickCount = 0
        eventHandler { _: TickEvent.Start ->
            if (tickCount++ % CHECK_INTERVAL_TICKS != 0) return@eventHandler
            refreshIfStale()
        }

        runAsync { fetch() }
    }

    private fun refreshIfStale() {
        val now = Instant.now()
        if (now.isBefore(fetchedAt.plusSeconds(REFRESH_SECONDS))) return
        if (now.isBefore(retryAfter)) return
        runAsync { fetch() }
    }

    private fun fetch() {
        if (Instant.now().isBefore(retryAfter)) return
        if (!fetching.compareAndSet(false, true)) return

        try {
            val coordinates = override ?: location ?: obtainLocation()?.also { location = it }
            if (coordinates == null) {
                backOff()
                return
            }

            val (latitude, longitude) = coordinates
            val current = obtainCurrentConditions(latitude, longitude)
            if (current == null) {
                backOff()
                return
            }

            conditions = current
            fetchedAt = Instant.now()
            retryDelaySeconds = MIN_RETRY_SECONDS
        } finally {
            fetching.set(false)
        }
    }

    private fun backOff() {
        retryAfter = Instant.now().plusSeconds(retryDelaySeconds)
        LOGGER.warn("Failed to fetch the current weather, retrying in {} seconds", retryDelaySeconds)
        retryDelaySeconds = (retryDelaySeconds * 2).coerceAtMost(MAX_RETRY_SECONDS)
    }

    private fun fetchJson(url: String): JsonElement? {
        return try {
            JsonUtils.parseFromUrl(url)
        } catch (e: Exception) {
            LOGGER.error("Failed to fetch JSON from {}", url, e)
            null
        }
    }

    private fun obtainLocation(): Pair<Double, Double>? {
        val json = fetchJson("http://ip-api.com/json") as? JsonObject
        if (json == null) {
            LOGGER.error("Failed to obtain JSON from ip-api.com")
            return null
        }

        if (!json.has("lat") || !json.has("lon")) {
            LOGGER.error("ip-api.com did not return latitude and longitude values")
            return null
        }

        return try {
            json.get("lat").asDouble to json.get("lon").asDouble
        } catch (e: Exception) {
            LOGGER.error("Failed to read the latitude and longitude from the ip-api.com JSON", e)
            null
        }
    }

    private fun obtainCurrentConditions(latitude: Double, longitude: Double): Conditions? {
        val json = fetchJson(
            "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,is_day"
        ) as? JsonObject
        if (json == null) {
            LOGGER.error("Failed to obtain JSON from open-meteo.com")
            return null
        }

        val current = json.getAsJsonObject("current")
        if (current == null) {
            LOGGER.error("open-meteo.com did not return the current conditions")
            return null
        }

        return try {
            Conditions(
                code = WeatherCode.fromCode(current.get("weather_code").asInt),
                temperature = current.get("temperature_2m").asDouble,
                apparentTemperature = current.get("apparent_temperature").asDouble,
                humidity = current.get("relative_humidity_2m").asInt,
                windSpeed = current.get("wind_speed_10m").asDouble,
                precipitation = current.get("precipitation").asDouble,
                isDay = current.get("is_day").asInt != 0,
            )
        } catch (e: Exception) {
            LOGGER.error("Failed to parse the current conditions from open-meteo.com", e)
            null
        }
    }
}
