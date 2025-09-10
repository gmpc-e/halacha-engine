package com.elad.halachatime.core

import com.elad.halachatime.core.model.Place
import com.elad.halachatime.core.model.ZmanRequest
import com.elad.halachatime.core.engine.ZmanResolver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ZmanResolverSmokeTest {

    @Test
    fun `compute returns non-empty results and methodMap`() {
        val date = LocalDate.of(2025, 9, 9)
        val req = ZmanRequest(
            date = date,
            place = Place("Test", 31.778, 35.235, 800.0, "Asia/Jerusalem"),
            presetKey = "GRA"
        )
        val res = ZmanResolver.compute(req)

        val results = res::class.members.first { it.name == "results" }.call(res) as Map<*, *>
        val methodMap = res::class.members.first { it.name == "methodMap" }.call(res) as Map<*, *>

        assertTrue(results.isNotEmpty(), "results should not be empty")
        assertTrue(methodMap.isNotEmpty(), "methodMap should not be empty")
    }
}