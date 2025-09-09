package com.elad.halachatime.core.engine

import com.elad.halachatime.core.model.Place
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToLong

object ShaotZmaniyot {

    enum class Mode { GRA, MGA }

    data class Result(
        val shaahMillis: Long,
        val dakaMillis: Long,
        val meta: Map<String, String>
    )

    fun compute(
        date: LocalDate,
        place: Place,
        mode: Mode,
        alosDeg: Double = 16.1,
        tzaisDeg: Double = 8.5
    ): Result {
        val zoneId = ZoneId.of(place.timeZoneId)
        val tz: TimeZone = TimeZone.getTimeZone(zoneId)
        val gl = GeoLocation(place.name, place.latitude, place.longitude, place.elevationMeters, tz)
        val czc = ComplexZmanimCalendar(gl)

        val cal: Calendar = GregorianCalendar(tz).apply {
            set(date.year, date.monthValue - 1, date.dayOfMonth, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        czc.calendar = cal

        val (startMs, endMs, meta) = when (mode) {
            Mode.GRA -> {
                val sr = czc.sunrise ?: return zero("sunrise/sunset unavailable")
                val ss = czc.sunset ?: return zero("sunrise/sunset unavailable")
                Triple(sr.time, ss.time, mapOf("mode" to "GRA", "start" to "getSunrise", "end" to "getSunset"))
            }
            Mode.MGA -> {
                val alos = alosByDegrees(czc, alosDeg)
                    ?: return zero("alos $alosDeg° unsupported; try 16.1, 18, 19, 19.8, or 26")
                val tzais = tzaisByDegrees(czc, tzaisDeg)
                    ?: return zero("tzais $tzaisDeg° unsupported; try 7.083, 8.5, 16.1, 18, 19.8, or 26")
                Triple(
                    alos.time,
                    tzais.time,
                    mapOf("mode" to "MGA", "start" to "alos$alosDeg°", "end" to "tzais$tzaisDeg°")
                )
            }
        }

        val dayLen = endMs - startMs
        if (dayLen <= 0) return zero("non-positive day length")
        val shaah = (dayLen / 12.0).roundToLong()
        val daka = (shaah / 60.0).roundToLong()
        return Result(shaahMillis = shaah, dakaMillis = daka, meta = meta)
    }

    private fun zero(reason: String) = Result(0L, 0L, mapOf("error" to reason))

    private fun alosByDegrees(czc: ComplexZmanimCalendar, degrees: Double) = whenClosest(degrees,
        16.1 to { czc.alos16Point1Degrees },
        18.0 to { czc.alos18Degrees },
        19.0 to { czc.alos19Degrees },
        19.8 to { czc.alos19Point8Degrees },
        26.0 to { czc.alos26Degrees },
    )

    private fun tzaisByDegrees(czc: ComplexZmanimCalendar, degrees: Double) = whenClosest(degrees,
        3.7 to { czc.tzaisGeonim3Point7Degrees },
        3.8 to { czc.tzaisGeonim3Point8Degrees },
        5.95 to { czc.tzaisGeonim5Point95Degrees },
        3.65 to { czc.tzaisGeonim3Point65Degrees },
        3.676 to { czc.tzaisGeonim3Point676Degrees },
        4.61 to { czc.tzaisGeonim4Point61Degrees },
        4.37 to { czc.tzaisGeonim4Point37Degrees },
        5.88 to { czc.tzaisGeonim5Point88Degrees },
        4.8 to { czc.tzaisGeonim4Point8Degrees },
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

    /** choose a supported method whose degree is “close enough” to the requested input */
    private inline fun <T> whenClosest(target: Double, vararg options: Pair<Double, () -> T?>, eps: Double = 1e-3): T? {
        // exact/close match first
        options.forEach { (deg, f) -> if (abs(target - deg) <= eps) return f() }
        // otherwise pick the numerically closest supported degree
        val best = options.minByOrNull { (deg, _) -> abs(target - deg) } ?: return null
        return best.second()
    }
}
