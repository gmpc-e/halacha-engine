package com.elad.halacha.rest
import io.ktor.server.request.receiveText
import com.elad.halachatime.core.engine.ShaotZmaniyot
import com.elad.halachatime.core.engine.ZmanIntrospector
import com.elad.halachatime.core.engine.ZmanResolver
import com.elad.halachatime.core.model.Place
import com.elad.halachatime.core.model.ZmanRequest
import com.elad.halachatime.core.presets.BoardPreset
import com.elad.halachatime.core.presets.PresetRegistry
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.*
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/** Public application module used by both main() and tests */
fun Application.halachaModule(
    registry: PresetRegistry = PresetRegistry.fromDefaultLocations()
) {
    install(ContentNegotiation) {
        jackson {
            // Needs rest-api dependency: jackson-datatype-jsr310
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }

        // --- Profiles (strict, v2 JSON only) ---
        route("/profiles") {
            get {
                val profiles: List<BoardPreset> = tryListProfiles(registry)
                val payload = profiles.map { p -> mapOf("key" to p.key, "displayName" to p.displayName) }
                call.respond(payload)
            }
            get("{key}") {
                val key = call.parameters["key"].orEmpty()
                val profile: BoardPreset? = getProfileCompat(registry, key)
                if (profile == null) call.respond(HttpStatusCode.NotFound, mapOf("error" to "Profile '$key' not found"))
                else call.respond(profile)
            }
            post("/validate/preset") {
                val body = call.receiveText()
                val sizeKb = body.toByteArray(Charsets.UTF_8).size / 1024.0
                val report = com.elad.halachatime.core.presets.PresetValidator.validateString(body)

                val who = report.detectedKey ?: "unknown"
                call.application.environment.log.info("[Validate] Received preset key='{}' size={}KB valid={} items={}",
                    who, String.format("%.1f", sizeKb), report.valid, report.itemCount)

                report.warnings.forEach {
                    call.application.environment.log.warn("[Validate] {}: {}", it.pointer, it.message)
                }
                report.errors.forEach {
                    call.application.environment.log.error("[Validate] {}: {}", it.pointer, it.message)
                }

                call.respond(report)
            }
        }

        // --- Zmanim (format=utc|local|both), versioned via ?scheme=v2 (strict: no fallback) ---
        get("/zmanim") {
            val q = call.request.queryParameters
            val date = q["date"]?.let { LocalDate.parse(it) }
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "date (yyyy-MM-dd) required"))

            val lat = q["lat"]?.toDoubleOrNull() ?: 31.778
            val lon = q["lon"]?.toDoubleOrNull() ?: 35.235
            val elev = q["elev"]?.toDoubleOrNull() ?: 800.0
            val tzId = q["tz"] ?: "Asia/Jerusalem"
            val name = q["name"] ?: "Custom"

            val presetKey = q["preset"] ?: "GRA"
            val format = (q["format"] ?: "both").lowercase()
            val scheme = (q["scheme"] ?: "").lowercase()
            val explain = q["explain"]?.toBoolean() ?: false
            val place = Place(name, lat, lon, elev, tzId)

            val res = ZmanResolver.compute(ZmanRequest(date, place, presetKey))

            if (scheme == "v2") {
                val preset: BoardPreset? = getProfileCompat(registry, presetKey)
                if (preset == null) {
                    return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf(
                            "error" to "Preset not found",
                            "presetKey" to presetKey,
                            "hint" to "Check -Dpreset.dir / PRESET_DIR and the JSON's 'key'."
                        )
                    )
                }
                if (preset.items.isEmpty()) {
                    return@get call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        mapOf(
                            "error" to "Preset has no items",
                            "presetKey" to presetKey,
                            "hint" to "JSON must define 'items': [...]. ('zmanim' is accepted via @JsonAlias and mapped to 'items'.)"
                        )
                    )
                }

                val items: List<ZmanItemV2> = buildItemsV2Strict(preset, res, explain)
                val shaped = when (format) {
                    "utc" -> items.map { it.copy(local = null) }
                    "local" -> items.map { it.copy(utc = null) }
                    else -> items
                }

                val payload = ZmanimResponseV2(
                    schemeVersion = "v2",
                    profileKey = presetKey,
                    meta = ZmanMeta(
                        date = date.toString(),
                        lat = lat, lon = lon, elevationM = elev, tz = tzId
                    ),
                    items = shaped
                )
                return@get call.respond(payload)
            }

            // ---- Legacy/default payload (kept for now) ----
            val payload: Any = when (format) {
                "utc" -> mapOf(
                    "preset" to getString(res, "preset"),
                    "place" to getAny(res, "place"),
                    "date" to getString(res, "date"),
                    "results" to getMapAny(res, "results"),
                    "methodMap" to getMapAny(res, "methodMap"),
                    "meta" to getAny(res, "meta")
                )
                "local" -> mapOf(
                    "preset" to getString(res, "preset"),
                    "place" to getAny(res, "place"),
                    "date" to getString(res, "date"),
                    "resultsLocal" to getMapAny(res, "resultsLocal"),
                    "methodMap" to getMapAny(res, "methodMap"),
                    "meta" to getAny(res, "meta")
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

            val lat = q["lat"]?.toDoubleOrNull() ?: 31.778
            val lon = q["lon"]?.toDoubleOrNull() ?: 35.235
            val elev = q["elev"]?.toDoubleOrNull() ?: 800.0
            val tzId = q["tz"] ?: "Asia/Jerusalem"
            val name = q["name"] ?: "Custom"
            val format = (q["format"] ?: "both").lowercase()

            val place = Place(name, lat, lon, elev, tzId)
            val raw = ZmanIntrospector.computeAll(date, place) // Map<Any, Date?>

            val zoneId = ZoneId.of(tzId)
            val fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            val utcMap = raw.mapKeys { (k, _) -> k.toString() }.mapValues { (_, d) -> d?.toInstant()?.toString() }
            val localMap = raw.mapKeys { (k, _) -> k.toString() }.mapValues { (_, d) -> d?.toInstant()?.atZone(zoneId)?.format(fmt) }

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

            val lat = q["lat"]?.toDoubleOrNull() ?: 31.778
            val lon = q["lon"]?.toDoubleOrNull() ?: 35.235
            val elev = q["elev"]?.toDoubleOrNull() ?: 800.0
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

        // --- Debug: where are presets coming from?
        get("/debug/presets") {
            val fromEnv = System.getenv("PRESET_DIR")
            val fromProp = System.getProperty("preset.dir")
            val dir = fromProp ?: fromEnv
            val files = dir?.let { java.io.File(it).listFiles { f -> f.isFile && f.name.endsWith(".json") } }?.map { it.name } ?: emptyList()
            val registryInfo = runCatching {
                val reg = if (dir.isNullOrBlank()) PresetRegistry.fromDefaultLocations() else PresetRegistry(listOf(java.nio.file.Path.of(dir)))
                val method = PresetRegistry::class.declaredFunctions.firstOrNull { it.name in setOf("listProfiles","list","all") && it.parameters.size == 1 }
                method?.isAccessible = true
                val listed = method?.call(reg) as? List<BoardPreset> ?: emptyList()
                mapOf("count" to listed.size, "keys" to listed.map { it.key })
            }.fold(onSuccess = { it }, onFailure = { ex ->
                mapOf("error" to (ex.message ?: ex::class.simpleName.orEmpty()))
            })
            call.respond(
                mapOf(
                    "env_PRESET_DIR" to fromEnv,
                    "prop_preset.dir" to fromProp,
                    "dirExists" to (dir?.let { java.io.File(it).exists() } ?: false),
                    "jsonFiles" to files,
                    "registry" to registryInfo
                )
            )
        }
    }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val externalDirs = resolveExternalPresetDirs()
    val registry =
        if (externalDirs.isEmpty()) PresetRegistry.fromDefaultLocations()
        else PresetRegistry(externalDirs)

    embeddedServer(Netty, port = port) {
        halachaModule(registry)
    }.start(wait = true)
}

/* ---------- V2 response models ---------- */

data class ZmanimResponseV2(
    val schemeVersion: String,
    val profileKey: String,
    val meta: ZmanMeta,
    val items: List<ZmanItemV2>
)

data class ZmanMeta(
    val date: String,
    val lat: Double,
    val lon: Double,
    val elevationM: Double,
    val tz: String
)

data class ZmanItemV2(
    val id: String,
    val label: Map<String, String>?,
    val utc: String?,
    val local: String?,
    val resolver: Any? = null,          // echoed only when explain=true
    val kosherJavaMethod: String? = null // echoed only when explain=true
)

/* ---------- Helpers ---------- */

private fun resolveExternalPresetDirs(): List<Path> {
    val fromEnv = System.getenv("PRESET_DIR")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
    val fromProp = System.getProperty("preset.dir")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
    return listOfNotNull(fromEnv, fromProp).filter { Files.exists(it) }
}

private fun tryListProfiles(registry: PresetRegistry): List<BoardPreset> {
    val f = PresetRegistry::class.declaredFunctions.firstOrNull {
        it.name in setOf("listProfiles", "list", "all") && it.parameters.size == 1
    } ?: return emptyList()
    f.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return runCatching { f.call(registry) as List<BoardPreset> }.getOrElse { emptyList() }
}

private fun getProfileCompat(registry: PresetRegistry, key: String): BoardPreset? {
    val f = PresetRegistry::class.declaredFunctions.firstOrNull {
        it.name in setOf("getProfile", "get") && it.parameters.size == 2
    } ?: return null
    f.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return runCatching { f.call(registry, key) as BoardPreset? }.getOrNull()
}

private fun buildItemsV2Strict(
    preset: BoardPreset,
    res: Any,
    explain: Boolean
): List<ZmanItemV2> {
    val results      = toStringKeyed(getMapAny(res, "results"))
    val resultsLocal = toStringKeyed(getMapAny(res, "resultsLocal"))
    val methodMap    = toStringKeyed(getMapAny(res, "methodMap"))

    return preset.items
        .sortedBy { it.order ?: Int.MAX_VALUE }
        .map { bi ->
            val id = bi.id
            val label = bi.label
            val utc = results[id]?.toString()
            val local = resultsLocal[id]?.toString()
            val resolverEcho = if (explain) bi.resolver else null
            val kj = if (explain) (bi.kosherJavaMethod ?: methodMap[id]?.toString()) else null

            ZmanItemV2(
                id = id,
                label = label,
                utc = utc,
                local = local,
                resolver = resolverEcho,
                kosherJavaMethod = kj
            )
        }
}

private fun getAny(instance: Any, name: String): Any? =
    instance::class.memberProperties.firstOrNull { it.name == name }?.let { (it as KProperty1<Any, *>).get(instance) }

private fun getString(instance: Any, name: String): String? = (getAny(instance, name) as? String)

@Suppress("UNCHECKED_CAST")
private fun getMapAny(instance: Any, name: String): Map<Any?, Any?> =
    (getAny(instance, name) as? Map<Any?, Any?>) ?: emptyMap()

private fun toStringKeyed(src: Map<Any?, Any?>): Map<String, Any?> =
    if (src.isEmpty()) emptyMap()
    else buildMap(src.size) { for ((k, v) in src) put(k?.toString() ?: "null", v) }