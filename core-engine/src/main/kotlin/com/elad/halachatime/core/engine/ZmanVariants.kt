package com.elad.halachatime.core.engine
import com.elad.halachatime.core.model.Place
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import kotlin.math.abs

/**
 * Compute Alos/Tzais in 2 modes:
 *  - Degrees: map to specific ComplexZmanimCalendar methods (e.g. 16.1°, 18°, 19.8°, 26° for Alos;
 *    7.083°, 8.5°, 16.1°, 18°, 19.8°, 26° for Tzais).
 *  - Minutes: offset from SEA-LEVEL sunrise/sunset by +/- X minutes.
 *
 * Notes:
 *  - Minutes mode is based on SEA-LEVEL sunrise/sunset, per Zmanim API guidance. Elevation impacts
 *    sunrise/sunset only; degree-based twilight is independent of elevation.
 */
object ZmanVariants {

    sealed class Variant {
        data class Degrees(val degrees: Double) : Variant()
        data class Minutes(val minutes: Double) : Variant()
    }

    data class Result(
        val alos: Date?,             // dawn
        val tzais: Date?,            // nightfall
        val meta: Map<String, String>
    )

    fun computeAlos(
        date: LocalDate,
        place: Place,
        variant: Variant
    ): Pair<Date?, Map<String, String>> {
        val (czc, tz) = czcFor(date, place)
        return when (variant) {
            is Variant.Degrees -> {
                val d = alosByDegrees(czc, variant.degrees)
                d to mapOf("alosMode" to "DEGREES", "degrees" to variant.degrees.toString(), "method" to (d?.javaClass?.name ?: "Date"))
            }
            is Variant.Minutes -> {
                // SEA-LEVEL sunrise minus X minutes
                val sea = czc.seaLevelSunrise ?: return null to mapOf("error" to "seaLevelSunrise unavailable")
                val d = Date(sea.time - (variant.minutes * 60_000.0).toLong())
                d to mapOf("alosMode" to "MINUTES", "minutes" to variant.minutes.toString(), "base" to "seaLevelSunrise")
            }
        }
    }

    fun computeTzais(
        date: LocalDate,
        place: Place,
        variant: Variant
    ): Pair<Date?, Map<String, String>> {
        val (czc, tz) = czcFor(date, place)
        return when (variant) {
            is Variant.Degrees -> {
                val d = tzaisByDegrees(czc, variant.degrees)
                d to mapOf("tzaisMode" to "DEGREES", "degrees" to variant.degrees.toString(), "method" to (d?.javaClass?.name ?: "Date"))
            }
            is Variant.Minutes -> {
                // SEA-LEVEL sunset plus X minutes
                val sea = czc.seaLevelSunset ?: return null to mapOf("error" to "seaLevelSunset unavailable")
                val d = Date(sea.time + (variant.minutes * 60_000.0).toLong())
                d to mapOf("tzaisMode" to "MINUTES", "minutes" to variant.minutes.toString(), "base" to "seaLevelSunset")
            }
        }
    }

    fun compute(
        date: LocalDate,
        place: Place,
        alos: Variant,
        tzais: Variant
    ): Result {
        val (alosDate, ameta) = computeAlos(date, place, alos)
        val (tzaisDate, tmeta) = computeTzais(date, place, tzais)
        return Result(
            alos = alosDate,
            tzais = tzaisDate,
            meta = ameta + tmeta
        )
    }

    /* ---------- internals ---------- */

    private fun czcFor(date: LocalDate, place: Place): Pair<ComplexZmanimCalendar, TimeZone> {
        val zoneId = ZoneId.of(place.timeZoneId)
        val tz: TimeZone = TimeZone.getTimeZone(zoneId)
        val gl = GeoLocation(place.name, place.latitude, place.longitude, place.elevationMeters, tz)
        val czc = ComplexZmanimCalendar(gl)
        val cal: Calendar = GregorianCalendar(tz).apply {
            set(date.year, date.monthValue - 1, date.dayOfMonth, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        czc.calendar = cal
        return czc to tz
    }

    // map supported Alos degrees to CZC methods
    private fun alosByDegrees(czc: ComplexZmanimCalendar, degrees: Double) = whenClosest(degrees,
        16.1 to { czc.alos16Point1Degrees },
        18.0 to { czc.alos18Degrees },
        19.0 to { czc.alos19Degrees },
        19.8 to { czc.alos19Point8Degrees },
        26.0 to { czc.alos26Degrees },
    )

    // map supported Tzais degrees to CZC methods
    private fun tzaisByDegrees(czc: ComplexZmanimCalendar, degrees: Double) = whenClosest(degrees,
        3.65 to { czc.tzaisGeonim3Point65Degrees },
        3.676 to { czc.tzaisGeonim3Point676Degrees },
        3.7 to { czc.tzaisGeonim3Point7Degrees },
        3.8 to { czc.tzaisGeonim3Point8Degrees },
        4.37 to { czc.tzaisGeonim4Point37Degrees },
        4.61 to { czc.tzaisGeonim4Point61Degrees },
        4.8 to { czc.tzaisGeonim4Point8Degrees },
        5.88 to { czc.tzaisGeonim5Point88Degrees },
        5.95 to { czc.tzaisGeonim5Point95Degrees },
        6.45 to { czc.tzaisGeonim6Point45Degrees },
        7.083 to { czc.tzaisGeonim7Point083Degrees },
        7.67 to { czc.tzaisGeonim7Point67Degrees },
        8.5 to { czc.tzaisGeonim8Point5Degrees },
        9.3 to { czc.tzaisGeonim9Point3Degrees },
        9.75 to { czc.tzaisGeonim9Point75Degrees },
        16.1 to { czc.tzais16Point1Degrees },
        18.0 to { czc.tzais18Degrees },
        19.8 to { czc.tzais19Point8Degrees },
        26.0 to { czc.tzais26Degrees },
    )

    private inline fun <T> whenClosest(
        target: Double,
        vararg options: Pair<Double, () -> T?>,
        eps: Double = 1e-3
    ): T? {
        options.forEach { (deg, f) -> if (abs(target - deg) <= eps) return f() }
        val best = options.minByOrNull { (deg, _) -> abs(target - deg) } ?: return null
        return best.second()
    }
}
