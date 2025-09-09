package com.elad.halachatime.core.engine

import com.elad.halachatime.core.engine.ZmanVariants.Variant
import com.elad.halachatime.core.model.Place
import com.kosherjava.zmanim.ComplexZmanimCalendar
import com.kosherjava.zmanim.util.GeoLocation
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import kotlin.math.abs

class ZmanVariantsTest {

    private val date = LocalDate.of(2025, 9, 9)
    private val tzId = "Asia/Jerusalem"
    private val place = Place(
        name = "Jerusalem",
        latitude = 31.778,
        longitude = 35.235,
        elevationMeters = 800.0, // for visible sunrise/sunset; minutes mode ignores elevation (sea-level base)
        timeZoneId = tzId
    )

    private fun czc(): ComplexZmanimCalendar {
        val tz = TimeZone.getTimeZone(ZoneId.of(tzId))
        val gl = GeoLocation(place.name, place.latitude, place.longitude, place.elevationMeters, tz)
        val c = ComplexZmanimCalendar(gl)
        val cal = GregorianCalendar(tz).apply {
            set(date.year, date.monthValue - 1, date.dayOfMonth, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        c.calendar = cal
        return c
    }

    private fun close(a: Date?, b: Date?, seconds: Long = 2L): Boolean {
        if (a == null || b == null) return false
        return abs(a.time - b.time) <= seconds * 1000
    }

    @Test
    fun `tzais 72 minutes equals sea-level sunset plus 72m`() {
        val c = czc()
        val seaSunset = c.seaLevelSunset
        assertNotNull(seaSunset, "seaLevelSunset should exist for this date/place")

        val (tz72, _) = ZmanVariants.computeTzais(date, place, Variant.Minutes(72.0))
        assertNotNull(tz72, "tzais 72m should compute")

        val expected = Date(seaSunset!!.time + 72L * 60_000L)
        assertTrue(close(tz72, expected, seconds = 1L), "tzais72m should equal sea-level sunset + 72m")
    }

    @Test
    fun `alos 72 minutes equals sea-level sunrise minus 72m`() {
        val c = czc()
        val seaSunrise = c.seaLevelSunrise
        assertNotNull(seaSunrise, "seaLevelSunrise should exist for this date/place")

        val (a72, _) = ZmanVariants.computeAlos(date, place, Variant.Minutes(72.0))
        assertNotNull(a72, "alos 72m should compute")

        val expected = Date(seaSunrise!!.time - 72L * 60_000L)
        assertTrue(close(a72, expected, seconds = 1L), "alos72m should equal sea-level sunrise - 72m")
    }

    @Test
    fun `degrees ordering sanity - tzais 7 083deg earlier than 8 5deg earlier than 18deg`() {
        val (t7083, _) = ZmanVariants.computeTzais(date, place, Variant.Degrees(7.083))
        val (t85, _)   = ZmanVariants.computeTzais(date, place, Variant.Degrees(8.5))
        val (t18, _)   = ZmanVariants.computeTzais(date, place, Variant.Degrees(18.0))

        assertNotNull(t7083); assertNotNull(t85); assertNotNull(t18)
        assertTrue(t7083!!.before(t85), "7.083° should be earlier than 8.5°")
        assertTrue(t85!!.before(t18),   "8.5° should be earlier than 18°")
    }

    @Test
    fun `degrees ordering sanity - alos 19 8deg earlier than 16 1deg`() {
        val (a198, _) = ZmanVariants.computeAlos(date, place, Variant.Degrees(19.8))
        val (a161, _) = ZmanVariants.computeAlos(date, place, Variant.Degrees(16.1))

        assertNotNull(a198); assertNotNull(a161)
        assertTrue(a198!!.before(a161), "Alos 19.8° should be earlier (darker) than Alos 16.1°")
    }
}
