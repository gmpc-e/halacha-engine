package com.elad.halachatime.core.engine

import com.elad.halachatime.core.model.*
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

/** Computes core zmanim (GRA-ish defaults) using ComplexZmanimCalendar. */
object ZmanResolver {

    fun compute(req: ZmanRequest): ZmanResults {
        val zoneId = ZoneId.of(req.place.timeZoneId)
        val tz: TimeZone = TimeZone.getTimeZone(zoneId)
        val gl = GeoLocation(
            req.place.name, req.place.latitude, req.place.longitude, req.place.elevationMeters, tz
        )
        val czc = ComplexZmanimCalendar(gl)

        // Set local date (noon to avoid DST boundary edge cases)
        val cal = GregorianCalendar(tz).apply {
            set(req.date.year, req.date.monthValue - 1, req.date.dayOfMonth, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        czc.calendar = cal

        val utc = linkedMapOf<ZmanMethodKey, Instant?>()
        val method = linkedMapOf<ZmanMethodKey, String>()

        // Dawn / Misheyakir / Sunrise
        utc[ZmanMethodKey.ALOT_HASHACHAR] = czc.alos16Point1Degrees?.toInstant()
        method[ZmanMethodKey.ALOT_HASHACHAR] = "getAlos16Point1Degrees"

        utc[ZmanMethodKey.MISHEYAKIR] = czc.misheyakir11Point5Degrees?.toInstant()
        method[ZmanMethodKey.MISHEYAKIR] = "getMisheyakir11Point5Degrees"

        utc[ZmanMethodKey.NETZ] = czc.sunrise?.toInstant()
        method[ZmanMethodKey.NETZ] = "getSunrise"

        // Sof Zmanim (GRA)
        utc[ZmanMethodKey.SHEMA_END] = czc.sofZmanShmaGRA?.toInstant()
        method[ZmanMethodKey.SHEMA_END] = "getSofZmanShmaGRA"

        utc[ZmanMethodKey.TEFILLAH_END] = czc.sofZmanTfilaGRA?.toInstant()
        method[ZmanMethodKey.TEFILLAH_END] = "getSofZmanTfilaGRA"

        // Midday / Mincha / Plag
        utc[ZmanMethodKey.CHATZOT] = czc.chatzos?.toInstant()
        method[ZmanMethodKey.CHATZOT] = "getChatzos"

        utc[ZmanMethodKey.MINCHA_GEDOLA] = czc.minchaGedola?.toInstant()
        method[ZmanMethodKey.MINCHA_GEDOLA] = "getMinchaGedola"

        utc[ZmanMethodKey.MINCHA_KETANA] = czc.minchaKetana?.toInstant()
        method[ZmanMethodKey.MINCHA_KETANA] = "getMinchaKetana"

        utc[ZmanMethodKey.PLAG] = czc.plagHamincha?.toInstant()
        method[ZmanMethodKey.PLAG] = "getPlagHamincha"

        // Sunset / Tzeit (default 8.5°)
        utc[ZmanMethodKey.SHKIA] = czc.sunset?.toInstant()
        method[ZmanMethodKey.SHKIA] = "getSunset"

        utc[ZmanMethodKey.TZEIT] = czc.tzaisGeonim8Point5Degrees?.toInstant()
        method[ZmanMethodKey.TZEIT] = "getTzaisGeonim8Point5Degrees"

        // Local strings
        val fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val local = utc.mapValues { (_, inst) -> inst?.atZone(zoneId)?.format(fmt) }

        return ZmanResults(
            preset = req.presetKey,
            place = req.place,
            date = req.date.toString(),
            results = utc,
            resultsLocal = local,
            methodMap = method,
            meta = mapOf("engine" to "KosherJava ComplexZmanimCalendar", "version" to "v1")
        )
    }
}
