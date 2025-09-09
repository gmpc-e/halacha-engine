package com.elad.halacha.rest

import com.elad.halachatime.core.engine.ShaotZmaniyot
import com.elad.halachatime.core.engine.ZmanIntrospector
import com.elad.halachatime.core.engine.ZmanResolver
import com.elad.halachatime.core.model.Place
import com.elad.halachatime.core.model.ZmanRequest
import com.elad.halachatime.core.presets.PresetRegistry
import com.elad.halachatime.core.presets.BoardPreset
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.jvm.isAccessible

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val externalDirs = resolveExternalPresetDirs()
    val registry = if (externalDirs.isEmpty()) PresetRegistry.fromDefaultLocations() else PresetRegistry(externalDirs)

    embeddedServer(Netty, port = port) {
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                call.respond(HttpStatusCode.InternalServerError, mapOf(
                    "error" to (cause.message ?: "internal error"),
                    "type" to (cause::class.simpleName ?: "Throwable")
                ))
            }
        }
        install(ContentNegotiation) {
            jackson {
                registerModule(JavaTimeModule())
                disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            }
        }

        routing {
            get("/health") { call.respond(mapOf("status" to "ok")) }

            // --- Profiles ---
            route("/profiles") {
                get {
                    val profiles: List<BoardPreset> = listProfilesCompat(registry)
                    val payload = profiles.map { p -> mapOf("key" to p.key, "displayName" to p.displayName) }
                    call.respond(payload)
                }
                get("{key}") {
                    val key = call.parameters["key"].orEmpty()
                    val profile: BoardPreset? = getProfileCompat(registry, key)
                    if (profile == null) call.respond(HttpStatusCode.NotFound, mapOf("error" to "Profile '$key' not found"))
                    else call.respond(profile)
                }
            }

            // --- Zmanim (format=utc|local|both) ---
            get("/zmanim") {
                val q = call.request.queryParameters
                val date = q["date"]?.let { LocalDate.parse(it) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "date (yyyy-MM-dd) required"))
                val lat = q["lat"]?.toDoubleOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "lat required"))
                val lon = q["lon"]?.toDoubleOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "lon required"))
                val elev = q["elev"]?.toDoubleOrNull() ?: 0.0
                val tzId = q["tz"] ?: "Asia/Jerusalem"
                val preset = q["preset"] ?: "GRA"
                val format = (q["format"] ?: "both").lowercase()

                val place = Place(q["name"] ?: "Custom", lat, lon, elev, tzId)
                val res = ZmanResolver.compute(ZmanRequest(date, place, preset))

                val payload: Any = when (format) {
                    "utc" -> mapOf(
                        "preset" to res.preset, "place" to res.place, "date" to res.date,
                        "results" to res.results, "methodMap" to res.methodMap, "meta" to res.meta
                    )
                    "local" -> mapOf(
                        "preset" to res.preset, "place" to res.place, "date" to res.date,
                        "resultsLocal" to res.resultsLocal, "methodMap" to res.methodMap, "meta" to res.meta
                    )
                    else -> res
                }
                call.respond(payload)
            }

            // --- All CZC methods (format=utc|local|both) ---
            get("/zmanim/all-methods") {
                val q = call.request.queryParameters
                val date = q["date"]?.let { LocalDate.parse(it) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "date (yyyy-MM-dd) required"))
                val lat = q["lat"]?.toDoubleOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "lat required"))
                val lon = q["lon"]?.toDoubleOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "lon required"))
                val elev = q["elev"]?.toDoubleOrNull() ?: 0.0
                val tzId = q["tz"] ?: "Asia/Jerusalem"
                val name = q["name"] ?: "Custom"
                val format = (q["format"] ?: "both").lowercase()

                val place = Place(name, lat, lon, elev, tzId)
                val raw = ZmanIntrospector.computeAll(date, place) // Map<String, Date?>

                val zoneId = ZoneId.of(tzId)
                val fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                val utcMap = raw.mapValues { (_, d) -> d?.toInstant()?.toString() }
                val localMap = raw.mapValues { (_, d) -> d?.toInstant()?.atZone(zoneId)?.format(fmt) }

                val payload = when (format) {
                    "utc" -> mapOf(
                        "date" to date.toString(),
                        "place" to mapOf("name" to name, "lat" to lat, "lon" to lon, "elevationMeters" to elev, "timeZoneId" to tzId),
                        "count" to utcMap.size,
                        "methods" to utcMap,
                        "meta" to mapOf("format" to "utc", "engine" to "ComplexZmanimCalendar")
                    )
                    "local" -> mapOf(
                        "date" to date.toString(),
                        "place" to mapOf("name" to name, "lat" to lat, "lon" to lon, "elevationMeters" to elev, "timeZoneId" to tzId),
                        "count" to localMap.size,
                        "methods" to localMap,
                        "meta" to mapOf("format" to "local", "engine" to "ComplexZmanimCalendar")
                    )
                    else -> mapOf(
                        "date" to date.toString(),
                        "place" to mapOf("name" to name, "lat" to lat, "lon" to lon, "elevationMeters" to elev, "timeZoneId" to tzId),
                        "count" to localMap.size,
                        "methodsUtc" to utcMap,
                        "methodsLocal" to localMap,
                        "meta" to mapOf("format" to "both", "engine" to "ComplexZmanimCalendar")
                    )
                }
                call.respond(payload)
            }

            // --- Sha'ot/Dakot Zmaniyot ---
            get("/zmanim/shaot") {
                val q = call.request.queryParameters
                val date = q["date"]?.let { LocalDate.parse(it) }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "date (yyyy-MM-dd) required"))
                val lat = q["lat"]?.toDoubleOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "lat required"))
                val lon = q["lon"]?.toDoubleOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "lon required"))
                val elev = q["elev"]?.toDoubleOrNull() ?: 0.0
                val tzId = q["tz"] ?: "Asia/Jerusalem"
                val alosDeg = q["alosDeg"]?.toDoubleOrNull() ?: 16.1
                val tzaisDeg = q["tzaisDeg"]?.toDoubleOrNull() ?: 8.5
                val name = q["name"] ?: "Custom"

                val place = Place(name, lat, lon, elev, tzId)
                val gra = ShaotZmaniyot.compute(date, place, ShaotZmaniyot.Mode.GRA)
                val mga = ShaotZmaniyot.compute(date, place, ShaotZmaniyot.Mode.MGA, alosDeg, tzaisDeg)

                fun pretty(ms: Long) = if (ms <= 0) null else {
                    val minutes = ms / 60000.0
                    mapOf(
                        "millis" to ms,
                        "minutes" to "%.3f".format(minutes),
                        "hh:mm:ss" to String.format(
                            "%02d:%02d:%02d",
                            (ms / 3600000),
                            (ms % 3600000) / 60000,
                            (ms % 60000) / 1000
                        )
                    )
                }

                call.respond(
                    mapOf(
                        "date" to date.toString(),
                        "place" to mapOf("name" to name, "lat" to lat, "lon" to lon, "elevationMeters" to elev, "timeZoneId" to tzId),
                        "GRA" to mapOf("shaahZmanit" to pretty(gra.shaahMillis), "dakaZmanit" to pretty(gra.dakaMillis), "meta" to gra.meta),
                        "MGA" to mapOf("shaahZmanit" to pretty(mga.shaahMillis), "dakaZmanit" to pretty(mga.dakaMillis), "meta" to (mga.meta + mapOf("alosDeg" to alosDeg, "tzaisDeg" to tzaisDeg)))
                    )
                )
            }
        }
    }.start(wait = true)
}

/* ---------- Helpers ---------- */

private fun resolveExternalPresetDirs(): List<Path> {
    val fromEnv = System.getenv("PRESET_DIR")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
    val fromProp = System.getProperty("preset.dir")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
    return listOfNotNull(fromEnv, fromProp).filter { Files.exists(it) }
}

private fun listProfilesCompat(registry: PresetRegistry): List<BoardPreset> {
    runCatching { PresetRegistry::class.declaredFunctions.firstOrNull { it.name == "listProfiles" } }
        .getOrNull()?.let { f -> f.isAccessible = true; @Suppress("UNCHECKED_CAST") return f.call(registry) as List<BoardPreset> }
    runCatching { PresetRegistry::class.declaredFunctions.firstOrNull { it.name == "list" } }
        .getOrNull()?.let { f -> f.isAccessible = true; @Suppress("UNCHECKED_CAST") return f.call(registry) as List<BoardPreset> }
    runCatching { PresetRegistry::class.declaredFunctions.firstOrNull { it.name == "all" } }
        .getOrNull()?.let { f -> f.isAccessible = true; @Suppress("UNCHECKED_CAST") return f.call(registry) as List<BoardPreset> }
    error("PresetRegistry missing listProfiles()/list()/all()")
}

private fun getProfileCompat(registry: PresetRegistry, key: String): BoardPreset? {
    runCatching { PresetRegistry::class.declaredFunctions.firstOrNull { it.name == "getProfile" && it.parameters.size == 2 } }
        .getOrNull()?.let { f -> f.isAccessible = true; @Suppress("UNCHECKED_CAST") return f.call(registry, key) as BoardPreset? }
    runCatching { PresetRegistry::class.declaredFunctions.firstOrNull { it.name == "get" && it.parameters.size == 2 } }
        .getOrNull()?.let { f -> f.isAccessible = true; @Suppress("UNCHECKED_CAST") return f.call(registry, key) as BoardPreset? }
    return null
}
