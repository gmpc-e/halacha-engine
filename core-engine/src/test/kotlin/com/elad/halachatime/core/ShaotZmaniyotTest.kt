package com.elad.halachatime.core

import com.elad.halachatime.core.engine.ShaotZmaniyot
import com.elad.halachatime.core.model.Place
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ShaotZmaniyotTest {

    @Test
    fun `MGA vs GRA shaah zmanit differs on typical day`() {
        val date = LocalDate.of(2025, 9, 9)
        val place = Place("Jerusalem", 31.778, 35.235, 800.0, "Asia/Jerusalem")

        val gra = ShaotZmaniyot.compute(date, place, ShaotZmaniyot.Mode.GRA)
        val mga = ShaotZmaniyot.compute(date, place, ShaotZmaniyot.Mode.MGA, 16.1, 8.5)

        assertTrue(gra.shaahMillis > 0)
        assertTrue(mga.shaahMillis > 0)
        assertNotEquals(gra.shaahMillis, mga.shaahMillis, "GRA and MGA should differ")
    }
}