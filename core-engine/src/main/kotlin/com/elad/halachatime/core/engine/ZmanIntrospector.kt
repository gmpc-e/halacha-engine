package com.elad.halachatime.core.engine

import com.elad.halachatime.core.model.Place
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import java.lang.reflect.Method
import java.time.LocalDate
import java.util.*

/**
 * Lists ALL zero-arg Date-returning methods on ComplexZmanimCalendar for a given date/place.
 * We keep it simple: return Map<methodName, Date?>; REST formats to UTC/local per query.
 */
object ZmanIntrospector {

    private val blacklist: Set<String> = setOf(
        "wait", "notify", "notifyAll", "equals", "hashCode", "getClass", "toString"
    )

    fun computeAll(date: LocalDate, place: Place): Map<String, Date?> {
        val tz = TimeZone.getTimeZone(place.timeZoneId)
        val gl = GeoLocation(place.name, place.latitude, place.longitude, place.elevationMeters, tz)
        val czc = ComplexZmanimCalendar(gl)

        val cal: Calendar = GregorianCalendar(tz).apply {
            set(date.year, date.monthValue - 1, date.dayOfMonth, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        czc.calendar = cal

        val out = linkedMapOf<String, Date?>()
        val methods: Array<Method> = czc.javaClass.methods
        methods.asSequence()
            .filter { it.parameterCount == 0 && it.returnType == Date::class.java }
            .filter { m -> blacklist.none { m.name.startsWith(it) } }
            .sortedBy { it.name.lowercase() }
            .forEach { m ->
                runCatching {
                    m.isAccessible = true
                    out[m.name] = m.invoke(czc) as Date?
                }.onFailure { out[m.name] = null }
            }
        return out
    }
}
